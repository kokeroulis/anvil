package com.squareup.anvil.sample

import com.squareup.anvil.annotations.ContributesSubcomponent
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.scopes.AppScope
import com.squareup.scopes.SingleIn
import dagger.BindsInstance
import dagger.Module
import dagger.Provides

class MyComponentScope private constructor()
class Argument(val value: Int)
class MappedArgument(val value: Int)

@SingleIn(MyComponentScope::class)
@ContributesSubcomponent(
  scope = MyComponentScope::class,
  parentScope = AppScope::class,
  modules = [MyComponentModule9::class]
)
interface MyComponent  {

  @ContributesTo(AppScope::class)
  interface ParentComponent {
    fun createSettingsFactory(): MyComponent.Factory
  }

  @ContributesSubcomponent.Factory
  interface Factory {

    fun create(@BindsInstance argument: Argument): MyComponent
  }
}

@Module
object MyComponentModule9 {

  @Provides
  fun arg(argument: Argument): MappedArgument = MappedArgument(argument.value)
}
